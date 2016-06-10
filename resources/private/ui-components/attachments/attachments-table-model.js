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
    return "icon";
  };

  self.isFiltered = function( data ) {
    return false;
  };

  self.inputId = function( index ) {
    return idPrefix + index;
  };

  var idFun = _.partial( _.flow, _.nthArg(), _.partialRight( _.get, "id" ));

  self.isApproved = service.isApproved;
  self.approve = idFun( service.approveAttachment );

  self.isRejected = service.isRejected;
  self.reject = idFun( service.rejectAttachment );

  self.remove = idFun( service.removeAttachment );

  var notNeededDict = _.reduce( self.attachments,
                                 function( acc, data ) {
                                   var obj = ko.utils.unwrapObservable( data );
                                   return _.set( acc, obj.id, self.disposedComputed(
                                     {read:  _.partial( _.flow( ko.utils.unwrapObservable,
                                                                service.isNotNeeded ),
                                                        data),
                                      write: _.partial( service.setNotNeeded,
                                                        obj.id)}) );
                                 },
                                {});

  self.notNeeded = idFun( _.partial( _.get, notNeededDict ));

  self.canDownload = self.disposedComputed( _.partial( _.some,
                                                       self.attachments,
                                                       self.hasFile ));
};
