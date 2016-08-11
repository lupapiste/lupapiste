LUPAPISTE.AttachmentsListingModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var legendTemplate = _.template( "<div class='like-btn'>"
                                   + "<i class='<%- icon %>'></i>"
                                   + "<span><%- text %></span>"
                                   + "</div>");
  var legend = [["lupicon-circle-check positive", "ok.title"],
                ["lupicon-circle-star primary", "new.title"],
                ["lupicon-circle-attention negative", "missing.title"],
                ["lupicon-circle-pen positive", "attachment.signed"],
                ["lupicon-circle-arrow-up positive", "application.attachmentSentDate"],
                ["lupicon-circle-stamp positive", "attachment.stamped"],
                ["lupicon-circle-section-sign positive", "attachment.verdict-attachment"],
                ["lupicon-lock primary", "attachment.not-public-attachment"]];

  self.legendHtml = _( legend )
    .map( function( leg ) {
      return legendTemplate( {icon: _.first( leg ),
                              text: loc( _.last( leg ))});
    })
    .value()
    .join("<br>");

  self.service = lupapisteApp.services.attachmentsService;
  self.disposedComputed(function() {
    console.log("AttachmentListingModel applicationId computed called");
    var id = self.service.applicationId(); // create dependency
    if (id)
      self.service.queryAll();
    else
      console.log("skipping queryAll, application id=", id);
  });

  self.groups = self.service.layout;

  var dispose = self.dispose;
  self.dispose = function() {
    self.service.changeScheduledNotNeeded();
    self.service.clearData();
    dispose();
  };
};
