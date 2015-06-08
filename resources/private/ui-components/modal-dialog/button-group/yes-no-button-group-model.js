LUPAPISTE.YesNoButtonGroupModel = function (params) {
  "use strict";
  var self = this;

  self.yesTitle = params.yesTitle || loc("yes");
  self.noTitle = params.noTitle || loc("no");

  self.yes = function() {
    if (_.isFunction(params.yesFn)) {
      params.yesFn();
    }
    hub.send("close-dialog");
  };

  self.no = function() {
    hub.send("close-dialog");
  };
};
