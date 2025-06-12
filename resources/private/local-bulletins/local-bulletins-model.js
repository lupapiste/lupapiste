LUPAPISTE.LocalBulletinsModel = function() {
  "use strict";
  var self = this;
  self.organization = pageutil.getURLParameter("organization");
  self.bulletinProcess = pageutil.getURLParameter("bulletinProcess") || "julkipano";
};
