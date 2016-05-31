LUPAPISTE.RamLinksModel = function( params) {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.attachment = params.attachment;
  self.attachmentId = self.attachment.id();
  self.service = lupapisteApp.services.ramService;
  self.links = ko.observableArray();

  self.showLinks = self.disposedPureComputed( function() {
    return _.size( self.links()) > 1
      && lupapisteApp.models.applicationAuthModel.ok( "ram-linked-attachments");
  });

  self.disposedComputed( function() {
    // We create dependency in order to make sure that the links table
    // updates if file is modified or deleted.
    // This also takes care of the initialization.
    if( self.attachment.versions()) {
      self.service.links( self.attachmentId, self.links );
    }
  });

  var approvalTemplate = _.template( "<%- user.firstName %>&nbsp;<%- user.lastName %> <%- time %>");

  self.approvalHtml = function( data ) {
    var approved = data.approved || {};
    return approved.value === "approved"
      ? approvalTemplate( {user: approved.user,
                           time: moment( approved.timestamp).format( "D.M.YYYY HH:mm")})
      : "-";
  };

  var ramLinkTemplate = _.template( "<a href='<%- url %>'><%= text %></a>");

  self.typeHtml = function( data ) {
    var text =  _.escape( loc (_.trim( data["ram-link"] )
                               ? "ram.type.ram"
                               : "ram.type.original"));
    return data.id === self.attachmentId
      ? text
      : ramLinkTemplate( {url: self.service.attachmentUrl( data.id ),
                          text: text});
  };
};
