LUPAPISTE.ResourceCalendarsModel = function () {
  "use strict";

  var self = this,
      calendarViewModel = null;

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
    self.items.splice(index, 1);
  };

  self.viewCalendar = function() {
    self.openCalendarViewDialog();
    if (!calendarViewModel) {
      calendarViewModel = calendarView.create($("#dialog-view-calendar .view-calendar-table"));
    }
    calendarViewModel.init({ source: this });
    LUPAPISTE.ModalDialog.open("#dialog-view-calendar");
  };

  self.openCalendarEditDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-edit-calendar");
  };

  self.openCalendarViewDialog = function() {
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