// Attachments table icon column contents. Used both by the regular
// attachments table and attachment multiselect template.
LUPAPISTE.StateIconsModel = function( params ) {
  "use strict";
  var self = this;

  var attachment = ko.unwrap( params.attachment );
  var service    = lupapisteApp.services.attachmentsService;

  self.stateIcons = function() {
    var hasFile = _.get(attachment, "latestVersion.fileId");
    var notNeeded = attachment.notNeeded();
    var approved = service.isApproved(attachment) && canVouch(attachment);
    var rejected = service.isRejected(attachment) && canVouch(attachment);

    function showSentToCaseManagementIcon(attachment) {
      return attachment.sent && !_.isEmpty(_.filter(lupapisteApp.models.application._js.transfers,
                                                    {type: "attachments-to-asianhallinta"}));
    }

    function showSentIcon(attachment) {
      return attachment.sent && !showSentToCaseManagementIcon(attachment);
    }

    function canVouch(attachment) {
      return hasFile && !service.isNotNeeded(attachment);
    }

    return _( [[approved, {css: "lupicon-circle-check positive", icon: "approved"}],
               [rejected || (!hasFile && !notNeeded), {css: "lupicon-circle-attention negative",
                                                       icon: "rejected"}],
               [ _.get( attachment, "signatures.0"), {css: "lupicon-circle-pen positive",
                                                icon: "signed"}],
               [attachment.state === "requires_authority_action", {css: "lupicon-circle-star primary",
                                                             icon: "state"}],
               [_.get(attachment, "latestVersion.stamped"), {css: "lupicon-circle-stamp positive",
                                                       icon: "stamped"}],
               [showSentIcon(attachment), {css: "lupicon-circle-arrow-up positive",
                                     icon: "sent"}],
               [showSentToCaseManagementIcon(attachment), {css: "lupicon-circle-arrow-up positive",
                                                     icon: "sent-to-case-management"}],
               [ko.unwrap(attachment.forPrinting), {css: "lupicon-circle-section-sign positive",
                                          icon: "for-printing"}],
               [_.get( attachment, "metadata.nakyvyys", "julkinen") !== "julkinen", {css: "lupicon-lock primary",
                                                                                     icon: "not-public"}]] )
      .filter(_.first)
      .map(_.last)
      .value();
  };
};
