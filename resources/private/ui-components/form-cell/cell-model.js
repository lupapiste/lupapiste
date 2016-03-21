// Simple model for cell components. Can be used as such for simple
// components or as a base model for more complex ones.
LUPAPISTE.CellModel = function( params ) {
  "use strict";
  var self = this;
  self.value = params.value;
  self.attributes = params.attr || {};
  self.params = _.omit( params, ["value", "attr"]);
};
