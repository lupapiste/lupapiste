LUPAPISTE.AuthAdminReservationTypesModel = function () {
  "use strict";

  var self = this;

  self.reservationType = ko.observable();
  self.items = lupapisteApp.services.calendarService.calendarQuery.reservationTypes;

  function EditReservationTypeModel() {
    var self = this;

    self.name = ko.observable();
    self.id = ko.observable();
    self.commandName = ko.observable();
    self.command = null;

    self.init = function(params) {
      self.commandName(params.commandName);
      self.command = params.command;
      self.id(util.getIn(params, ["source", "id"], ""));
      self.name(util.getIn(params, ["source", "name"], ""));
    };

    self.execute = function() {
      if (self.commandName() === "edit") {
        self.command(self.id(), self.name());
      } else {
        self.command(self.name());
      }
    };

    self.ok = ko.computed(function() {
      return !_.isBlank(self.name());
    });
  }
  self.editReservationTypeModel = new EditReservationTypeModel();

  self.editReservationType = function() {
    self.editReservationTypeModel.init({
      source: this,
      commandName: "edit",
      command: function(id, reservationType) {
        ajax
          .command("update-reservation-type", {reservationTypeId: id, name: reservationType})
          .success(function() {
            _.partial( hub.send, "calendarService::fetchOrganizationReservationTypes" );
            LUPAPISTE.ModalDialog.close();
          })
          .call();
      }
    });
    self.openReservationTypeDialog();
  };

  self.addReservationType = function() {
    self.editReservationTypeModel.init({
      commandName: "add",
      command: function(reservationType) {
        ajax
          .command("add-reservation-type-for-organization", {reservationType: reservationType})
          .success(function() {
            hub.send("calendarService::fetchOrganizationReservationTypes");
            LUPAPISTE.ModalDialog.close();
          })
          .call();
      }
    });
    self.openReservationTypeDialog();
  };

  self.deleteReservationType = function(reservationType) {
    ajax
      .command("delete-reservation-type", {reservationTypeId: reservationType.id})
      .success(hub.send("calendarService::fetchOrganizationReservationTypes"))
      .call();
  };

  self.openReservationTypeDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-edit-reservation-type");
  };

};