LUPAPISTE.BookAppointmentFilterModel = function (params) {

  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.authorities = params.model.authorities;
  self.selectedParty = params.model.selectedParty;
  self.reservationTypes = params.model.reservationTypes;
  self.selectedReservationType = params.model.selectedReservationType;
};