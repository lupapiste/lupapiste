LUPAPISTE.MessagePanelModel = function(params) {
  "use strict";
  var self = this;

  self.params = params;

  self.showMessagePanel = ko.computed(function() {
    return self.params.show() && !_.isEmpty(self.params.message());
  });

  self.dispose = function() {
    // self.dispose = self.showMessagePanel.dispose throws an error,
    // perhaps it is too eagerly optimized.
    self.showMessagePanel.dispose();
  };
};
