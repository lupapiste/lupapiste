LUPAPISTE.TargetedAttachmentsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.target = params.target;
  self.attachmentType = params.type;
  self.typeSelector = params.typeSelector;
  self.canAdd = params.canAdd || true;

  function dataView( target ) {
    return ko.mapping.toJS( _.pick( ko.unwrap( target ), ["id", "type"]));
  }

  self.attachments = self.disposedPureComputed( function() {
    return _.filter(lupapisteApp.services.attachmentsService.attachments(),
                              function(attachment) {
                                return _.isEqual(dataView(attachment().target),
                                                 dataView(self.target()));
                              });
  });

  self.canUpload = self.disposedPureComputed( function() {
    return lupapisteApp.models.globalAuthModel.ok( "upload-attachment");
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
