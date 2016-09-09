LUPAPISTE.TargetedAttachmentsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.target = params.target;
  self.attachmentType = params.type;
  self.typeSelector = params.typeSelector;
  self.canAdd = params.canAdd || true;

  var service = lupapisteApp.services.attachmentsService;

  function dataView( target ) {
    return ko.mapping.toJS( _.pick( ko.unwrap( target ), ["id", "type"]));
  }

  self.attachments = self.disposedPureComputed( function() {
    return _.filter(service.attachments(),
                    function(attachment) {
                      return _.isEqual(dataView(attachment().target),
                                       dataView(self.target()));
                    });
  });

  self.canUpload = self.disposedPureComputed( function() {
    return service.authModel.ok( "upload-attachment");
  });

  self.newAttachment = function() {
    attachment.initFileUpload({
      applicationId: lupapisteApp.models.application.id(),
      attachmentId: null,
      attachmentType: self.attachmentType(),
      typeSelector: self.typeSelector,
      target: self.target(),
      locked: lupapisteApp.models.currentUser.isAuthority()
    });
    LUPAPISTE.ModalDialog.open("#upload-dialog");
  };
};
