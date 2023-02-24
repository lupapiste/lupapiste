// Simple model for cell components. Can be used as such for simple
// components or as a base model for more complex ones.
LUPAPISTE.CellModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.value = params.value;
  self.attributes = params.attr || {};
  self.testId = params.testId;
  self.isRequired = self.disposedPureComputed( function () {
    return ko.unwrap( params.required ) && !ko.unwrap( self.value );
  });
  self.isInvalid = params.warning;
  self.params = _.omit( params, ["value", "attr", "testId",
                                 "required", "warning"]);
};
