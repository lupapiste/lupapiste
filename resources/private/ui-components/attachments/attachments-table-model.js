LUPAPISTE.AttachmentsTableModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  var assignmentService = lupapisteApp.services.assignmentService;
  var accordionService = lupapisteApp.services.accordionService;

  self.appModel = lupapisteApp.models.application;
  self.authModel = lupapisteApp.models.applicationAuthModel;

  self.upload = params.upload;
  self.selectableRows = params.options.selectableRows;
  self.pageModel = params.options.pageModel;

  self.toggleResell = service.toggleResell;
  self.authorities = accordionService.authorities;
  self.attachments = params.attachments;

  // ["column1", ...] => {"column1":true, ...} so we don't have to use _.includes in the template
  self.columns = _(params.options.columns)
    .map(function(column) { return [column, true]; })
    .fromPairs()
    .value();

  var idPrefix = _.uniqueId("at-input-");
  self.inputId = function(index) { return idPrefix + index; };

  self.isApproved = service.isApproved;
  self.isRejected = service.isRejected;
  self.isNotNeeded = service.isNotNeeded;
  self.isAuthority = lupapisteApp.models.currentUser.isAuthority;
  self.isResellable = service.isResellable;
  self.testState = function( attachment ) {
    return _.get( service.attachmentApproval( attachment), "state", "neutral");
  };

  self.hasFile = function(attachment) {
    return _.get(ko.utils.unwrapObservable(attachment), "latestVersion");
  };

  self.hasContents = function(attachment) {
    var contents = _.get(ko.utils.unwrapObservable(attachment), "contents", "")();
    return !_.isEmpty(_.trim(contents));
  };

  self.buildHash = function(attachment) {
    var applicationId = lupapisteApp.models.application._js.id;
    return pageutil.buildPageHash("attachment", applicationId, attachment.id);
  };

  self.remove = function(attachment) {
    var yesFn = function() {
      service.removeAttachment(attachment.id);
    };
    hub.send("show-dialog", {ltitle: "attachment.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: _.isEmpty(attachment.versions) ? "attachment.delete.message.no-versions" : "attachment.delete.message",
                                               yesFn: yesFn}});
  };

  self.approve = function(attachment) {
    service.approveAttachment(attachment.id);
  };

  self.reject = function(attachment) {
    if( service.isRejected( attachment )) {
      service.rejectAttachmentNoteEditorState( attachment.id );
    } else {
      service.rejectAttachment(attachment.id);
    }
  };

  function isAssignmentShownInTable(attachmentIds, assignment) {
    return assignment.targets.length === 1
      && assignment.targets[0].group === "attachments"
      && _.includes(attachmentIds, assignment.targets[0].id)
      && assignment.currentState.type !== "completed";
  }

  self.assignments = self.disposedPureComputed(function() {
    var attachmentIds = _.map(ko.unwrap(self.attachments), function(att) { return util.getIn(att, ["id"]); });
    if (assignmentService) {
      return  _(assignmentService.assignments())
        .filter(_.partial(isAssignmentShownInTable, attachmentIds))
        .groupBy("targets[0].id")
        .value();
    } else {
      return {};
    }
  }).extend({deferred: true});

  /*
   * Functions for selectable rows
   */
  self.getSelectRowFunction = function(row) {
    if (self.selectableRows && self.pageModel) {
      return function() {
        self.pageModel.selectRow(row);
      };
    }
    return null;
  };

  self.getRowSelected = function(row) {
    if (self.selectableRows && self.pageModel) {
      var selected = self.pageModel.getRowSelected(row);
      return selected || _.constant(false);
    }
    return _.constant(false);
  };

  /*
   * Functions for stamping
   */

  self.getRowStatus = function(row) {
    if (self.selectableRows && self.pageModel) {
      var status = self.pageModel.getRowStatus(row);
      return status || _.constant(false);
    }
    return _.constant(false);
  };

  self.isStampingMode = function() {
    if (self.pageModel) {
      var stampingModel = self.pageModel;
      var status = _.get(stampingModel, "status", _.constant(null))();
      return status && stampingModel && status === stampingModel.statusReady;
    }
    return false;
  };


};
