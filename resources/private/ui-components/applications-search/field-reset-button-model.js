// component for reseting input fields
//
// Params:
// field: observale to reset
//
LUPAPISTE.FieldResetButtonModel = function(params) {
  "use strict";

  var self = this;

  self.reset = function() {
    params.field(null);
  };

};
