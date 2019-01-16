LUPAPISTE.AttachmentsTableModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  var assignmentService = lupapisteApp.services.assignmentService;
  var accordionService = lupapisteApp.services.accordionService;

  self.appModel = lupapisteApp.models.application;
  self.authModel = lupapisteApp.models.applicationAuthModel;

  self.attachments = params.attachments;
  self.upload = params.upload;

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
      hub.send("track-click", {category:"Attachments", label: "", event: "deleteAttachmentFromListing"});
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

  self.toggleResell = service.toggleResell;

  self.authorities = accordionService.authorities;

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

};
