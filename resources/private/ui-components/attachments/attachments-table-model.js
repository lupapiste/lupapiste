LUPAPISTE.AttachmentsTableModel = function( params ) {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  self.attachments = params.attachmentInfos().attachments;

  var idPrefix = _.uniqueId( "at-input-");

  self.hasFile = function( data ) {
    return _.get( ko.utils.unwrapObservable(data), "latestVersion.fileId");
  };

  self.stateIcons = function( data ) {
    return service.isNotNeeded( data )
      ? []
      : _( [[self.isApproved, "lupicon-circle-check positive"],
            [self.isRejected, "lupicon-circle-attention negative"],
            [_.partialRight( _.get, "signed.0"), "lupicon-circle-pen positive"]])
      .map( function( xs ) {
        return _.first( xs )( data ) ? _.last( xs ) : false;
      })
      .filter()
      .value();

  };

  self.isFiltered = function( data ) {
    return false;
  };

  self.inputId = function( index ) {
    return idPrefix + index;
  };

  // When foo = idFun( fun ), then foo(data) -> fun(data.id)
  var idFun = _.partial( _.flow, _.nthArg(), _.partialRight( _.get, "id" ));

  self.isApproved = service.isApproved;
  self.approve = idFun( service.approveAttachment );

  self.isRejected = service.isRejected;
  self.reject = idFun( service.rejectAttachment );

  self.remove = idFun( service.removeAttachment );

  self.canDownload = self.disposedComputed( _.partial( _.some,
                                                       self.attachments,
                                                       self.hasFile ));

  self.toggleNotNeeded = function( data  ) {
    service.setNotNeeded( data.id, !data.notNeeded);
  };
  self.isNotNeeded = service.isNotNeeded;
};
