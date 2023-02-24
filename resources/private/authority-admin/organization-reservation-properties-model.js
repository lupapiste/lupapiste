LUPAPISTE.AuthAdminOrganizationReservationPropertiesModel = function () {
  "use strict";

  var self = this;

  self.defaultLocation = ko.observable("");
  self.defaultLocationIndicator = ko.observable().extend({notify: "always"});
  ko.computed(function() {
    var location = self.defaultLocation();
    if (self.initialized) {
      ajax.command("set-organization-default-reservation-location", {location: location})
        .success(_.partial(self.defaultLocationIndicator, {type: "saved"}))
        .error(_.partial(self.defaultLocationIndicator, {type: "err"}))
        .call();
    }
  });

  self.init = function(data) {
    self.defaultLocation(util.getIn(data.organization, ["reservations", "default-location"], ""));
    self.initialized = true;
  };

  self.load = function() {
    ajax.query("organization-by-user").success(self.init).call();
  };
};