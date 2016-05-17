LUPAPISTE.AuthAdminReservationTypesModel = function () {
  "use strict";

  var self = this;

  function NewReservationTypeModel() {
    var self = this;

    self.name = ko.observable();

    self.execute = function () {
      console.info("Adding reservation type: ", self.name());
      alert("TODO");
    };
  };

  self.newReservationTypeModel = new NewReservationTypeModel();

  self.openNewReservationTypeDialog = function () {
    LUPAPISTE.ModalDialog.open("#dialog-new-reservation-type");
  };

  self.items = ko.observableArray();

  self.init = function (data) {
    self.items(data.reservationTypes);
  };

  self.load = function () {
    ajax.query("reservation-types-for-organization")
      .success(function (d) {
        self.init(d);
      })
      .call();
  };
};