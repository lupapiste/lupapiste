LUPAPISTE.AttachmentsTableModel = function(attachments) {
  "use strict";

  var service = lupapisteApp.services.attachmentsService;

  function hasFile(attachment) {
    return _.get(ko.utils.unwrapObservable(attachment), "latestVersion.fileId");
  }


  function buildHash(attachment) {
    var applicationId = lupapisteApp.models.application._js.id;
    return pageutil.buildPageHash("attachment", applicationId, attachment.id);
  }

  function addFile(attachment) {
    hub.send( "add-attachment-file", {attachmentId: attachment.id,
                                      attachmentType: attachment.typeString(),
                                      attachmentGroup: attachment.group() });
  }

  function removeAttachment(attachment) {
    var yesFn = function() {
      hub.send("track-click", {category:"Attachments", label: "", event: "deleteAttachmentFromListing"});
      service.removeAttachment(attachment.id);
    };
    hub.send("show-dialog", {ltitle: "attachment.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: _.isEmpty(attachment.versions) ? "attachment.delete.message.no-versions" : "attachment.delete.message",
                                               yesFn: yesFn}});
  }

  function approveAttachment(attachment) {
    service.approveAttachment(attachment.id);
  }

  function rejectAttachment(attachment) {
    service.rejectAttachment(attachment.id);
  }

  var idPrefix = _.uniqueId("at-input-");
  var appModel = lupapisteApp.models.application;

  return {
    attachments: attachments,
    idPrefix: idPrefix,
    hasFile: hasFile,
    stateIcons: service.stateIcons,
    inputId: function(index) { return idPrefix + index; },
    isApproved: service.isApproved,
    approve: approveAttachment,
    isRejected: service.isRejected,
    reject: rejectAttachment,
    isNotNeeded: service.isNotNeeded,
    remove: removeAttachment,
    appModel: appModel,
    authModel: lupapisteApp.models.applicationAuthModel,
    buildHash: buildHash,
    addFile: addFile,
    isAuthority: lupapisteApp.models.currentUser.isAuthority
  };
};
