// Params [optional]:
// dropzoneSectionId: id for the section selector.
// [typeGroups]: type groups to be passed to attachment-batch
// [defaults]: defaults to be passed to attachment-batch
// [template]: alternative template for attachment list table (see attachments-table-template.html)
// [canAdd]: If false, the upload is disabled (default true). In
// addition, the bind-attachment auth is checked.
LUPAPISTE.TargetedAttachmentsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.componentTemplate = params.template || "targeted-attachments-default";
  self.defaults = params.defaults;
  self.typeGroups = params.typeGroups;
  self.disabledCols = params.disabledCols;
  var canAdd = _.isUndefined( params.canAdd ) || params.canAdd;

  var service = lupapisteApp.services.attachmentsService;

  var readOnly = self.disposedComputed( function() {
    return !(ko.unwrap(canAdd) && service.authModel.ok( "bind-attachment"));
  });

  self.upload = new LUPAPISTE.UploadModel(self,
                                          {allowMultiple:true,
                                           dropZone: "section#" + params.dropZoneSectionId,
                                           readOnly: readOnly});

  function dataView( target ) {
    return ko.mapping.toJS( _.pick( ko.unwrap( target ), ["id", "type"]));
  }

  self.attachments = self.disposedPureComputed( function() {
    return _.filter(service.attachments(),
                    function(attachment) {
                      return _.isEqual(dataView(attachment().target),
                                       dataView(self.defaults.target));
                    });
  });

  self.canDeleteAttachment = function(attachment) {
    return attachment.authModel.ok("delete-attachment");
  };

  self.deleteAttachment = function(attachmentId) {
    hub.send( "show-dialog",
              {ltitle: "attachment.delete.version.header",
               size: "medium",
               component: "yes-no-dialog",
               componentParams: {ltext: "attachment.delete.version.message",
                                 yesFn: _.partial(lupapisteApp.services.attachmentsService.removeAttachment, attachmentId)}});
  };
};
