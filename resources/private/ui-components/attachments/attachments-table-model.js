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

  function openAttachment(attachment) {
    var applicationId = lupapisteApp.models.application._js.id;
    pageutil.openPage("attachment", applicationId + "/" + attachment.id);
  }

  function stateIcons(attachment) {
    var data = ko.utils.unwrapObservable(attachment);
    var notNeeded = attachment.notNeeded();
    var file = hasFile(data);
    var approved = service.isApproved(data) && canVouch(data);
    var rejected = service.isRejected(data) && canVouch(data);

    return  _( [[approved, {css: "lupicon-circle-check positive", icon: "approved"}],
                [rejected || (!file && !notNeeded), {css: "lupicon-circle-attention negative", icon: "rejected"}],
                [ _.get( data, "signatories.0"), {css: "lupicon-circle-pen positive", icon: "signed"}],
                [data.state === "requires_authority_action", {css: "lupicon-circle-star primary", icon: "state"}],
                [data.stamped, {css: "lupicon-circle-stamp positive", icon: "stamped"}],
                [showSentIcon(data), {css: "lupicon-circle-arrow-up positive", icon: "sent"}],
                [showSentToCaseManagementIcon(data), {css: "lupicon-circle-arrow-up positive", icon: "sent-to-case-management"}]] )
      .filter(_.first)
      .map(_.last)
      .value();
  }

  var idPrefix = _.uniqueId("at-input-");
  var appModel = lupapisteApp.models.application;

  // When foo = idFun( fun ), then foo(data) -> fun(data.id)
  var idFun = _.partial( _.flow, _.nthArg(), _.partialRight( _.get, "id" ));

  return {
    attachments: attachments,
    idPrefix: idPrefix,
    hasFile: hasFile,
    stateIcons: stateIcons,
    inputId: function(index) { return idPrefix + index; },
    isApproved: service.isApproved,
    approve: idFun(service.approveAttachment),
    isRejected: service.isRejected,
    reject: idFun(service.rejectAttachment),
    isNotNeeded: service.isNotNeeded,
    remove: idFun(service.removeAttachment),
    appModel: appModel,
    authModel: lupapisteApp.models.applicationAuthModel,
    canVouch: canVouch,
    openAttachment: openAttachment
  };
};
