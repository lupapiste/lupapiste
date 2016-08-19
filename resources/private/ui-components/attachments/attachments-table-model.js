LUPAPISTE.AttachmentsTableModel = function(attachments) {
  "use strict";

  var service = lupapisteApp.services.attachmentsService;

  function hasFile(attachment) {
    return _.get(ko.utils.unwrapObservable(attachment), "latestVersion.fileId");
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

    return  _( [[approved, {icon: "lupicon-circle-check positive", type: "approved"}],
                [rejected || (!file && !notNeeded), {icon: "lupicon-circle-attention negative", type: "rejected"}],
                [ _.get( data, "signed.0"), {icon: "lupicon-circle-pen positive", type: "signed"}],
                [data.state === "requires_authority_action", {icon: "lupicon-circle-star primary", type: "state"}],
                [data.stamped, {icon: "lupicon-circle-stamp positive", type: "stamped"}]])
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
