LUPAPISTE.RadioFieldModel = function(params) {
  "use strict";
  params = params || {};

  // Construct super
  LUPAPISTE.InputFieldModel.call(this, params);

  this.selectedValue = params.selectedValue;
};
LUPAPISTE.RadioFieldModel.prototype = _.create(LUPAPISTE.InputFieldModel.prototype, {"constructor":LUPAPISTE.RadioFieldModel});
