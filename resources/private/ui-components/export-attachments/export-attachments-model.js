// [css]: class for buttons (default positive)
LUPAPISTE.ExportAttachmentsModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  function bindSystem( cmd, msg ) {
    return {show: self.disposedComputed( _.partial( lupapisteApp.models
                                                    .applicationAuthModel.ok,
                                                    cmd)),
            send: _.partial( hub.send, msg)};
  }

  self.buttonCss = _.set( {}, _.get( params, "css", "positive" ), true );
  self.backing = bindSystem( "move-attachments-to-backing-system",
                             "start-moving-attachments-to-backing-system");
  self.cm = bindSystem( "attachments-to-asianhallinta",
                        "start-moving-attachments-to-case-management");
};
