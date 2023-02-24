LUPAPISTE.RemoveButtonModel = function(params) {
  "use strict";
  var self = this;

  self.showRemove = ko.observable(false);

  self.removeFn = params.removeFn || _.noop;
};
