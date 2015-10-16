LUPAPISTE.ApplicationBulletinModel = function(params) {
  "use strict";

  var self = this;

  self.bulletin = ko.observable();

  ajax.query("bulletin", {bulletinId: params.bulletinId})
    .success(function(res) {
      if (res.bulletin.id) {
        self.bulletin(res.bulletin);
      } else {
        pageutil.openPage("bulletins");
      }
    })
    .call();

};
