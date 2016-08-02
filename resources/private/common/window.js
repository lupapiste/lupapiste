;(function($) {
 "use strict";

   LUPAPISTE.Window = function() {
    var self = this;
    self.serviceName = "windowService";

    var win = $(window);

    self.windowWidth = ko.observable(win.width());
    self.windowHeight = ko.observable(win.height());

    // listen widow change events
    win.resize(function() {
      self.windowWidth(win.width());
      self.windowHeight(win.height());
    });
  };

})(jQuery);

;(function() {
  "use strict";
  window.lupapisteWindow = new LUPAPISTE.Window();
})();
