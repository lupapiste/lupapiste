LUPAPISTE.AuthAdminReservationTypesModel = function () {
  "use strict";

  var self = this;

  self.newReservationType = ko.observable();

  self.addReservationType = function () {
    ajax.command("add-reservation-type-for-organization", { reservationType: self.newReservationType() })
      .success(function (data) {
        self.items(data.reservationTypes);
      })
      .call();
  }

  self.openNewReservationTypeDialog = function () {
    LUPAPISTE.ModalDialog.open("#dialog-new-reservation-type");
  };

  self.items = ko.observableArray();

  self.load = function () {
    ajax.query("reservation-types-for-organization")
      .success(function (data) {
        self.items(data.reservationTypes);
      })
      .call();
  };
};