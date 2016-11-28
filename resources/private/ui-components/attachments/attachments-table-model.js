LUPAPISTE.AttachmentsTableModel = function(attachments) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  var assignmentService = lupapisteApp.services.assignmentService;
  var accordionService = lupapisteApp.services.accordionService;

  self.appModel = lupapisteApp.models.application;

  self.authModel = lupapisteApp.models.applicationAuthModel;

  self.attachments = attachments;

  var idPrefix = _.uniqueId("at-input-");

  self.inputId = function(index) { return idPrefix + index; };

  self.isApproved = service.isApproved;
  self.isRejected = service.isRejected;
  self.isNotNeeded = service.isNotNeeded;
  self.isAuthority = lupapisteApp.models.currentUser.isAuthority;

  self.hasFile = function(attachment) {
    return _.get(ko.utils.unwrapObservable(attachment), "latestVersion.fileId");
  };

  self.buildHash = function(attachment) {
    var applicationId = lupapisteApp.models.application._js.id;
    return pageutil.buildPageHash("attachment", applicationId, attachment.id);
  };

  self.addFile = function(attachment) {
    hub.send( "add-attachment-file", {attachmentId: attachment.id,
                                      attachmentType: attachment.typeString(),
                                      attachmentGroup: attachment.group() });
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

  self.authorities = accordionService.authorities;

  self.assignments = self.disposedPureComputed(function() {
    var attachmentIds = _.map(attachments, function(att) { return util.getIn(att, ["id"]); });
    if (assignmentService) {
      return  _(assignmentService.assignments())
        .filter(function(assignment) {
          return assignment.target.group === "attachments"
            && _.includes(attachmentIds, assignment.target.id)
            && assignment.currentState.type !== "completed";
        })
        .groupBy("target.id")
        .value();
    } else {
      return {};
    }
  }).extend({deferred: true});

};
