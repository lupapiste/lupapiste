LUPAPISTE.ResourceCalendarsModel = function () {
  "use strict";

  var self = this;

  function NewReservationSlotModel() {
    var self = this;

    self.weekday = ko.observable();
    self.weekdayStr = ko.observable();
    self.startTime = ko.observable();
    self.commandName = ko.observable();
    self.command = null;

    self.init = function(params) {
      self.commandName(params.commandName);
      self.command = params.command;
      self.weekday(util.getIn(params, ["source", "weekday"], null));
      self.weekdayStr(util.getIn(params, ["source", "weekday", "str"], null));
      self.startTime(util.getIn(params, ["source", "startTime"], ""));
    };
  };

  self.newReservationSlotModel = new NewReservationSlotModel();
  self.newReservationSlot = function (weekday, time) {
    self.newReservationSlotModel.init({
      source: { startTime: time, weekday: weekday },
      commandName: 'create',
      command: function() {
        LUPAPISTE.ModalDialog.close();
      }
    });
    self.openNewReservationSlotDialog();
  };

  self.openNewReservationSlotDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-new-slot");
  };

  function EditCalendarModel() {
    var self = this;

    self.name = ko.observable();
    self.organization = ko.observable();

    self.commandName = ko.observable();
    self.command = null;

    self.init = function(params) {
      self.commandName(params.commandName);
      self.command = params.command;
      self.name(util.getIn(params, ["source", "name"], ""));
      self.organization(util.getIn(params, ["source", "organization"], ""));
    };

    self.execute = function() {
      self.command(self.name(), self.organization());
    };

    self.ok = ko.computed(function() {
      return !_.isBlank(self.name()) && !_.isBlank(self.organization());
    });
  }
  self.editCalendarModel = new EditCalendarModel();

  self.items = ko.observableArray();

  self.editCalendar = function(indexFn) {
    var index = indexFn();
    self.editCalendarModel.init({
      source: this,
      commandName: "edit",
      command: function(name, organization) {
        /*ajax
          .command("update-res-link", {index: index, url: url, nameFi: nameFi, nameSv: nameSv})
          .success(function() {
            self.load();
            LUPAPISTE.ModalDialog.close();
          })
          .call(); */

        // DUMMY DATA EDIT
        self.items.replace(self.items()[index], { name: name, organization: organization });

        LUPAPISTE.ModalDialog.close();
      }
    });
    self.openCalendarEditDialog();
  };

  self.addCalendar = function() {
    self.editCalendarModel.init({
      source: this,
      commandName: "add",
      command: function(name, organization) {
        /*ajax
          .command("update-res-link", {index: index, url: url, nameFi: nameFi, nameSv: nameSv})
          .success(function() {
            self.load();
            LUPAPISTE.ModalDialog.close();
          })
          .call(); */

        // DUMMY DATA EDIT
        self.items.push({ name: name, organization: organization });

        LUPAPISTE.ModalDialog.close();
      }
    });
    self.openCalendarEditDialog();
  };

  self.rmCalendar = function(indexFn) {
    // DUMMY DATA EDIT
    var index = indexFn();
    LUPAPISTE.ModalDialog.showDynamicYesNo(
            loc("areyousure"),
            loc("auth-admin.resource.calendar.delete.confirm"),
              {title: loc("yes"), fn: function() {
                self.items.splice(index, 1);
              }}
            );
    return false;
  };

  self.openCalendarEditDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-edit-calendar");
  };

  self.init = function(data) {
    var calendars = data.calendars;
    self.items(calendars || []);
  };

  self.load = function() {
    // DUMMY DATA
    self.init({
      calendars: [
        { name: "Tarkastaja Teppo", organization: "Rakennusvalvonta" }
      ]
    });
  };
};