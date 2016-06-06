LUPAPISTE.RollupButtonModel = function( params ) {
  "use strict";
  var self = this;
  self.open = params.open;
  self.text = params.ltext ? loc( params.ltext ) : params.text;
  self.css = _.defaults( {toggled: self.open}, params.css || {});
};
