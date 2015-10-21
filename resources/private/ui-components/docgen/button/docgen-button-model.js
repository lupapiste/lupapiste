LUPAPISTE.DocgenButtonModel = function(params) {
  "use strict";
  var self = this;

  self.id = params.id;
  self.clickFn = params.clickFn || _.noop;
  self.label = params.label;
  self.icon = params.icon;
  self.className = params.className || 'primary';
};