LUPAPISTE.AttachmentsListingModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.pageName = "attachments-listing";

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
                              text: loc( _.last( leg ))});
    })
    .value()
    .join("<br>");

  var service = lupapisteApp.services.attachmentsService;
  var appModel = lupapisteApp.models.application;

  var filterSet = service.getFilters( self.pageName );

  var attachments = service.attachments;

  var filteredAttachments = self.disposedPureComputed(function() {
    return filterSet.apply(attachments());
  });

  self.applicationId = appModel.id;

  self.authModel = lupapisteApp.models.applicationAuthModel;

  hub.send( "scrollService::follow", {hashRe: /\/attachments$/} );

  self.upload = new LUPAPISTE.UploadModel( self,
                                           {dropZone: "#application-attachments-tab",
                                            allowMultiple: true,
                                            readOnly: self.disposedPureComputed(function() {
                                              return !self.authModel.ok( "upload-attachment");
                                            }),
                                            badFileHandler: _.noop} );
  self.batchEmpty = self.disposedComputed( _.partial( lupapisteApp.services
                                                      .batchService.batchEmpty,
                                                      null, self.upload));

  // After attachment query
  function afterQuery( params ) {
    var id = _.get( params, "attachmentId");
    if( id && pageutil.lastSubPage() === "attachments" ) {
      pageutil.openPage( "attachment", appModel.id() + "/" + id);
    }
  }

  self.addEventListener( service.serviceName, {eventType: "query", triggerCommand: "upload-attachment"}, afterQuery );

  self.addEventListener(service.serviceName, {eventType: "update", commandName: "approve-attachment"}, function(params) {
    service.queryOne(params.attachmentId);
  });

  self.addEventListener(service.serviceName, {eventType: "update", commandName: "reject-attachment"}, function(params) {
    service.queryOne(params.attachmentId);
  });

  self.addEventListener(service.serviceName, "create", LUPAPISTE.ModalDialog.close);

  self.addEventListener(service.serviceName, "remove", util.showSavedIndicator);

  self.addEventListener(service.serviceName, "copy-user-attachments", util.showSavedIndicator);

  self.hasUnfilteredAttachments = self.disposedPureComputed(function() {
    return !_.isEmpty(attachments());
  });

  self.hasFilteredAttachments = self.disposedPureComputed(function() {
    return !_.isEmpty(filteredAttachments());
  });

  self.hasFilters = self.disposedPureComputed(function() {
    return !_.isEmpty(filterSet.filters());
  });

};
