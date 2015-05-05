LUPAPISTE.YesNoButtonGroupModel = function (params) {
  "use strict";
  var self = this;

  self.yesTitle = params.yesTitle || "yes";
  self.noTitle = params.noTitle || "no";

  self.yes = params.yesFn || function() { _.noop(); };

  self.no = function() {
    hub.send("close-dialog");
  };
};
