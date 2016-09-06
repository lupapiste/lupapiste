LUPAPISTE.AttachmentsTableModel = function(attachments) {
  "use strict";

  var service = lupapisteApp.services.attachmentsService,
      application = lupapisteApp.models.application._js;

  function hasFile(attachment) {
    return _.get(ko.utils.unwrapObservable(attachment), "latestVersion.fileId");
  }

  function showSentToCaseManagementIcon(attachment) {
    return attachment.sent && !_.isEmpty(_.filter(application.transfers, {type: "attachments-to-asianhallinta"}));
  }

  function showSentIcon(attachment) {
    return attachment.sent && !showSentToCaseManagementIcon(attachment);
  }

  function canVouch(attachment) {
    var att = ko.utils.unwrapObservable(attachment);
    return hasFile(att) && !service.isNotNeeded(attachment);
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

  function stateIcons(attachment) {
    var data = ko.utils.unwrapObservable(attachment);
    var notNeeded = attachment.notNeeded();
    var file = hasFile(data);
    var approved = service.isApproved(data) && canVouch(data);
    var rejected = service.isRejected(data) && canVouch(data);

    return  _( [[approved, {css: "lupicon-circle-check positive", icon: "approved"}],
                [rejected || (!file && !notNeeded), {css: "lupicon-circle-attention negative", icon: "rejected"}],
                [ _.get( data, "signatures.0"), {css: "lupicon-circle-pen positive", icon: "signed"}],
                [data.state === "requires_authority_action", {css: "lupicon-circle-star primary", icon: "state"}],
                [_.get(data, "latestVersion.stamped"), {css: "lupicon-circle-stamp positive", icon: "stamped"}],
                [showSentIcon(data), {css: "lupicon-circle-arrow-up positive", icon: "sent"}],
                [showSentToCaseManagementIcon(data), {css: "lupicon-circle-arrow-up positive", icon: "sent-to-case-management"}],
                [attachment.forPrinting(), {css: "lupicon-circle-section-sign positive", icon: "for-printing"}],
                [_.get( data, "metadata.nakyvyys", "julkinen") !== "julkinen", {css: "lupicon-lock primary", icon: "not-public"}]] )
      .filter(_.first)
      .map(_.last)
      .value();
  }

  var idPrefix = _.uniqueId("at-input-");
  var appModel = lupapisteApp.models.application;

  return {
    attachments: attachments,
    idPrefix: idPrefix,
    hasFile: hasFile,
    stateIcons: stateIcons,
    inputId: function(index) { return idPrefix + index; },
    isApproved: service.isApproved,
    approve: approveAttachment,
    isRejected: service.isRejected,
    reject: rejectAttachment,
    isNotNeeded: service.isNotNeeded,
    remove: removeAttachment,
    appModel: appModel,
    authModel: lupapisteApp.models.applicationAuthModel,
    canVouch: canVouch,
    buildHash: buildHash,
    addFile: addFile,
    isAuthority: lupapisteApp.models.currentUser.isAuthority
  };
};
