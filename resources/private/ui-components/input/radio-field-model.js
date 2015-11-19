LUPAPISTE.RadioFieldModel = function(params) {
  "use strict";
  params = params || {};

  // Construct super
  ko.utils.extend(this, new LUPAPISTE.InputFieldModel(params));

  this.selectedValue = params.selectedValue;
};
