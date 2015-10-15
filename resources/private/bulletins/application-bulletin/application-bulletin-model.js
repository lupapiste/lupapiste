LUPAPISTE.ApplicationBulletinModel = function(params) {
  "use strict";

  var self = this;

  self.bulletin = ko.observable();

  ajax.query("bulletin", {bulletinId: params.bulletinId})
    .success(function(d) {
      console.log(d.bulletin);
      self.bulletin(d.bulletin);
    })
    .call();

};
