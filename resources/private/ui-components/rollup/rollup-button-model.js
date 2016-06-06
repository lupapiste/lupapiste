LUPAPISTE.RollupButtonModel = function( params ) {
  "use strict";
  var self = this;
  ko.utils.extend (self, new LUPAPISTE.ComponentBaseModel() );

    self.open = params.open;
  self.text = params.ltext ? loc( params.ltext ) : params.text;
  self.css = self.disposedComputed( function() {
    return _.defaults( {toggled: self.open}, ko.unwrap( params.css || {} ));
  });
};
