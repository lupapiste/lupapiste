LUPAPISTE.LocalBulletinsModel = function() {
  "use strict";

  self.organization = ko.observable(pageutil.subPage());

  hub.onPageLoad("local-bulletins", function() {
    self.organization(pageutil.subPage());
  });

};