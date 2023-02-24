LUPAPISTE.RamLinksModel = function( params ) {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.attachment = params.attachment;
  self.attachmentId = self.attachment().id;
  self.service = lupapisteApp.services.ramService;
  self.links = ko.observableArray();

  self.showLinks = self.disposedComputed( function() {
    // There is always at least one link (the current attachment).
    // A lone link is shown in the table only if it as a RAM attachment.
    var count = _.size( self.links());
    return (count > 1 || (count === 1 &&  _.trim(_.first(self.links()).ramLink)))
      && lupapisteApp.models.applicationAuthModel.ok( "ram-linked-attachments");
  });

  function updateLinks() {
    // We create dependency in order to make sure that the links table
    // updates if file is modified or deleted.
    // This also takes care of the initialization.
    if( self.attachment().versions) {
      self.service.links( self.attachmentId, self.links );
    }
  }
  updateLinks();
  self.disposedSubscribe(self.attachment, updateLinks);

  var approvalTemplate = _.template( "<%- user.firstName %>&nbsp;<%- user.lastName %><br><%- time %>");
  var attachmentsService = lupapisteApp.services.attachmentsService;

  function approvedText( approval ) {
    return _.isNumber( approval.timestamp ) && _.isPlainObject( approval.user )
      ? approvalTemplate( {user: approval.user,
                           time: moment( approval.timestamp).format( "D.M.YYYY HH:mm")})
    : loc( "ok");
  }

  self.approvalHtml = function( data ) {
    var attachment = attachmentsService.getAttachment( data.id );
    var approval = attachmentsService.attachmentApproval( attachment );
    return attachmentsService.isApproved( attachment )
      ? approvedText( approval )
      : "-";
  };

  var ramLinkTemplate = _.template( "<a href='<%- url %>'><%- text %></a>");

  self.typeHtml = function( data ) {
    var text = loc (_.trim( data.ramLink ) ? "ram.type.ram" : "ram.type.original");
    return data.id === self.attachmentId
      ? text
      : ramLinkTemplate( {url: self.service.attachmentUrl( data.id ),
                          text: text});
  };
};
