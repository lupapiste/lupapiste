LUPAPISTE.ResourceCalendarsModel = function () {
  "use strict";

  var self = this;

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

  function ViewCalendarModel() {
    var self = this;

    self.name = ko.observable();
    self.organization = ko.observable();
    self.allowedCalendarSlots = ko.observableArray();

    self.init = function(params) {
      self.name(util.getIn(params, ["source", "name"], ""));
      self.organization(util.getIn(params, ["source", "organization"], ""));
      self.allowedCalendarSlots([
        { time: "7:00" },
        { time: "8:00" },
        { time: "9:00" },
        { time: "10:00" },
        { time: "11:00" },
        { time: "12:00" },
        { time: "13:00" }
      ]);
    };
  }
  self.viewCalendarModel = new ViewCalendarModel();

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

  self.viewCalendar = function(indexFn) {
    self.viewCalendarModel.init({
      source: this
    });
    self.openCalendarViewDialog();
  };

  self.openCalendarEditDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-edit-calendar");
  };

  self.openCalendarViewDialog = function() {
    LUPAPISTE.ModalDialog.open("#dialog-view-calendar");
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