LUPAPISTE.SubmitButtonModel = function(params) {
  "use strict";
  var self = this;

  self.submitFn = params.submitFn || _.noop;
  self.name = params.name;
  self.value = params.value;
  self.id = params.id;
  self.lLabel = params.lLabel;
  self.lSubmitTitle = params.lSubmitTitle;

  self.validated = ko.validatedObservable([self.value]);

  self.disableButton = ko.pureComputed(function() {
    return !self.validated.isValid() || !self.value();
  });
};
