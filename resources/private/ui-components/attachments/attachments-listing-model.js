// A component for listing attachments in accordions.
// Can be customised for use in different views that require a list of the attachments attached to an application.
//
// Params [optional]:
// upload                   The module used for uploading files to the service
// pageName                 The name of the page this list is on; used to identify the page when using the filters service
// options                  An object containing (mostly) optional parameters as follows:
//   .columns               Array of strings containing the columns to be displayed in the attachment table
//   [.selectableRows]      The rows in the list can be selected; used for e.g. choosing which attachments to stamp
//   [.downloadableRows]    Whether the "Download all" button is visible on the accordions
//   [.pageModel]           Used for accessing the topmost view model; e.g. when accessing the functions for selecting rows
//   [.accordionIndicators] Whether accordion status bars should be stylized according to attachment status
//   [.attachmentWhiteList] Array of strings containing the id:s of the attachments shown
LUPAPISTE.AttachmentsListingModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.upload = params.upload;
  self.pageName = params.pageName;
  self.options = params.options;

  var service = lupapisteApp.services.attachmentsService;
  self.attachments = self.disposedPureComputed(function() {
    var filtered = service.attachments();
    if (!_.isUndefined(self.options.attachmentWhiteList)) {
      filtered = _.filter(filtered,function(a) { return _.includes(self.options.attachmentWhiteList(), a().id); });
    }
    return filtered;
  });

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
                ["lupicon-lock primary", "attachment.not-public-attachment"],
                ["lupicon-archives positive", "attachment.archived"]];
  self.legendHtml = _( legend )
    .map( function( leg ) {
      return legendTemplate( {icon: _.first( leg ),
                              text: loc( _.last( leg ))}); })
    .value()
    .join("<br>");

  var filterSet = service.getFilters( self.pageName );
  var filteredAttachments = self.disposedPureComputed(function() {
    return filterSet.apply(self.attachments());
  });

  self.hasUnfilteredAttachments = self.disposedPureComputed(function() {
    return !_.isEmpty(self.attachments());
  });
  self.hasFilteredAttachments = self.disposedPureComputed(function() {
    return !_.isEmpty(filteredAttachments());
  });
  self.hasFilters = self.disposedPureComputed(function() {
    return !_.isEmpty(filterSet.filters());
  });

};
