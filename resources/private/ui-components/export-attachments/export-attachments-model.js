// [css]: class for buttons (default positive)
LUPAPISTE.ExportAttachmentsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.testId = self.disposedPureComputed( function() {
    var authOk = lupapisteApp.models.applicationAuthModel.ok;

    if( authOk( "move-attachments-to-backing-system" )) {
      return "export-attachments-to-backing-system";
    }
    if( authOk( "attachments-to-asianhallinta" )) {
      return "export-attachments-to-asianhallinta";
    }
  });

  self.buttonClass = _.get( params, "css", "primary" );

  self.openPage = function() {
    pageutil.openPage( "send-attachments", pageutil.hashApplicationId() );
  };
};
