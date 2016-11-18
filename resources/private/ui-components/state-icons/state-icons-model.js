// Attachments table icon column contents. Used both by the regular
// attachments table and attachment multiselect template.
LUPAPISTE.StateIconsModel = function( params ) {
  "use strict";
  var self = this;

  var attachment = ko.unwrap( params.attachment );
  var service    = lupapisteApp.services.attachmentsService;
  var transfers  = lupapisteApp.models.application._js.transfers;

  function sentToCaseManagement(attachment) {
    return attachment.sent && !_.isEmpty(_.filter(transfers, {type: "attachments-to-asianhallinta"}));
  }

  function sent(attachment) {
    return attachment.sent && !sentToCaseManagement(attachment);
  }

  function hasFile(attachment) {
    return _.get(attachment, "latestVersion.fileId");
  }

  function canVouch(attachment) {
    return hasFile(attachment) && !service.isNotNeeded(attachment);
  }

  function versionString(m) {
    return [_.get(m, "version.major"), _.get(m, "version.minor")].join(".");
  }

  function signed(attachment) {
    return _(attachment.signatures).map(versionString).includes(versionString(attachment.latestVersion));
  }

  function missingFile(attachment) {
    return (!hasFile(attachment) && !attachment.notNeeded());
  }

  function rejected(attachment) {
    return service.isRejected(attachment) && canVouch(attachment);
  }

  function needsAttention(attachment) {
    return rejected(attachment) || missingFile(attachment);
  }

  function approved(attachment) {
    return service.isApproved(attachment) && canVouch(attachment);
  }

  function requiresAuthorityAction(attachment) {
    return attachment.state === "requires_authority_action";
  }

  function stamped(attachment) {
    return _.get(attachment, "latestVersion.stamped");
  }

  function forPrinting(attachment) {
    return ko.unwrap(attachment.forPrinting);
  }

  function notPublic(attachment) {
    return _.get( attachment, "metadata.nakyvyys", "julkinen") !== "julkinen";
  }

  self.stateIcons = function() {
    return _( [[approved,                {css: "lupicon-circle-check positive",
                                          icon: "approved"}],
               [needsAttention,          {css: "lupicon-circle-attention negative",
                                          icon: "rejected"}],
               [signed,                  {css: "lupicon-circle-pen positive",
                                          icon: "signed"}],
               [requiresAuthorityAction, {css: "lupicon-circle-star primary",
                                          icon: "state"}],
               [stamped,                 {css: "lupicon-circle-stamp positive",
                                          icon: "stamped"}],
               [sent,                    {css: "lupicon-circle-arrow-up positive",
                                          icon: "sent"}],
               [sentToCaseManagement,    {css: "lupicon-circle-arrow-up positive",
                                          icon: "sent-to-case-management"}],
               [forPrinting,             {css: "lupicon-circle-section-sign positive",
                                          icon: "for-printing"}],
               [notPublic,               {css: "lupicon-lock primary",
                                          icon: "not-public"}]] )
      .filter(function(icon) { return _.first(icon)(attachment); })
      .map(_.last)
      .value();
  };

};
