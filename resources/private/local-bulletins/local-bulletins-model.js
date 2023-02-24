LUPAPISTE.LocalBulletinsModel = function() {
  "use strict";
  var self = this;
  self.organization = ko.observable(pageutil.getURLParameter("organization"));
};