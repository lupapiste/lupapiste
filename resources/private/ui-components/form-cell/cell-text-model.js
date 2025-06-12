// Text input cell.
// Special params [optional]:
// [immediate]: if truthy then the value is updated immediately when
// user types something (KO textInput binding). Default false.
LUPAPISTE.CellTextModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.CellModel( params ));

  self.immediate = params.immediate;
  self.attributes = _.defaults( self.attributes, {type: "text"});
};
