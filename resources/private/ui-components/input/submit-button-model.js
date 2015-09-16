LUPAPISTE.SubmitButtonModel = function(params) {
  "use strict";
  var self = this;

  self.submitFn = params.submitFn || _.noop;
  self.name = params.name;
  self.value = params.value;
  self.id = params.id;
  self.lLabel = params.lLabel;
  self.lSubmitTitle = params.lSubmitTitle;

  self.disabled = ko.pureComputed(function() {
    return !self.value();
  });
};
