LUPAPISTE.MessagePanelModel = function(params) {
  "use strict";
  var self = this;

  self.params = params;

  self.showMessagePanel = ko.computed(function() {
    return self.params.show() && !_.isEmpty(self.params.message());
  });
};
