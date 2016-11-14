LUPAPISTE.SearchFieldModel = function(params) {
  "use strict";
  var self = this;

  var value = params.value || ko.observable();

  self.maxlength = params.maxlength;

  self.isSelected = ko.observable();

  self.throttledValue = ko.observable();

  self.throttledValue.subscribe(function(val) {
    value(val);
  });
};
