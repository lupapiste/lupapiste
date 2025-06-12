/*

 */
LUPAPISTE.StampModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  // Utility function for stampableAttachment below
  function allVersionsStamped(versions) {
    return _.every(versions, function(v) {
      return v.stamped;
    });
  }

  // Returns true if this is an attachment that can be stamped
  function stampableAttachment(a) {
    var ct = "";
    if (a.latestVersion) {
      ct = a.latestVersion.contentType;
    }
    var archived = util.getIn(a, ["metadata", "tila"]) === "arkistoitu";
    return !allVersionsStamped(a.versions) && _.includes(LUPAPISTE.config.stampableMimes, ct) && !archived;
  }

  // Adds stamping & selection fields to the attachment
  function enhanceAttachment(a) {
    var selected = _(_.dropRightWhile(a.versions, function(version) {
      return version.stamped;
    })).last();

    var stamped = _.get( a, "latestVersion.stamped");
    a.contentType = selected.contentType;
    a.filename = selected.filename;
    a.version = {major: selected.version.major, minor: selected.version.minor};
    a.size = selected.size;
    a.selected = ko.observable(a.forPrinting && !stamped);
    a.status = ko.observable("");
    a.restamp = stamped;
    a.stamped = ko.observable(stamped);
    a.latestVersion.fileId = ko.observable(a.latestVersion.fileId);
    return a;
  }

                             // Start:  Cancel:  Ok:
  self.statusInit      = 0;  //   -       -       -
  self.statusReady     = 1;  //   +       +       -
  self.statusStarting  = 2;  //   -       -       -
  self.statusRunning   = 3;  //   -       -       -
  self.statusDone      = 4;  //   -       -       +
  self.statusError     = 5;  //   -       +       -

  // Init
  self.application = params.application;
  self.attachments = ko.observableArray();
  self.enhancedAttachments = ko.observableArray();
  self.status = ko.observable();

  function typeLoc(att) {
    return loc(["attachmentType", util.getIn(att, ["type", "type-group"]), util.getIn(att, ["type", "type-id"])]);
  }

  function modified(att) {
    return -util.getIn(att, ["modified"]);
  }


  // Handle the filters here so they don't slow down the computed below
  var filterSet = lupapisteApp.services.attachmentsService.getFilters( "stamp-attachments" );

  // Filter the attachments to only include files that can be stamped
  self.disposedComputed(function() {
    var attachments = _(params.attachments())
      .map(ko.unwrap)
      .sortBy([typeLoc, modified])
      .value();
    var filteredFiles = _(filterSet.apply(ko.mapping.toJS(attachments))).filter(stampableAttachment).value();
    self.attachments(filteredFiles);
  });

  // Given as a parameter to the attachments-listing component to whitelist the attachments with
  self.listedAttachments = self.disposedPureComputed(function() {
    return _(self.attachments()).map(function(a) { return a.id; }).value();
  });

  // Groups the filtered files into post- and pre-verdict attachments
  self.disposedComputed(function() {
    self.enhancedAttachments(_.map(self.attachments(), enhanceAttachment));
    self.status(self.statusReady);
  });

  self.selectedFiles = self.disposedComputed(function() {
    return _.filter(self.enhancedAttachments(), function(a) { return a.selected(); });
  });
  self.allSelected = self.disposedComputed(function() {
    return _.every(self.enhancedAttachments(), function(a) { return a.selected(); });
  });

  self.stampButtonText = self.disposedComputed(function() {
    var count = _.size(_.filter(self.enhancedAttachments(), function(a) { return a.selected(); }));
    return loc("stamp.start-count", count);
  });

  self.getFileById = function(id) {
    return _.find(self.enhancedAttachments(), function(f) { return id === f.id; });
  };

  self.getRowStatus = function(row) {
    return _.get(self.getFileById(row.id), "status");
  };

  self.getRowSelected = function(row) {
    return _.get(self.getFileById(row.id), "selected");
  };

  self.selectRow = function(row) {
    var selected = self.getRowSelected(row);
    if ( self.status() < self.statusStarting && selected ) {
      selected(!selected());
    }
  };

  self.getSelectedObservablesByFileId = function(fileIds) {
    return _(fileIds)
      .map(function (id) { return _.get(self.getFileById(id), "selected"); })
      .filter(_.isFunction)
      .value();
  };

  // Used by the listing-accordion for selecting/deselecting a group of files
  self.selectFilesById = function(fileIds) {
    if (self.status() < self.statusStarting) {
      // If any of the files is unselected, select them all; otherwise unselect all
      var newValue = false;
      var observables = self.getSelectedObservablesByFileId(fileIds);
      if (_(observables).some(function(selected) { return !selected(); })) {
        newValue = true;
      }
      // Set all the files as selected/unselected
      _(observables).forEach(function(selected) {
        selected(newValue);
      });
    }
  };





  /*
   * Stamp parameter functionality
   */
  function calculateTransparency(value) {
    return Math.round(255 * value / 100.0);
  }

  var transparencies = _.map([0,20,40,60,80], function(v) {
    return {text: loc(["stamp.transparency", v.toString()]), value: calculateTransparency(v)};
  });
  self.transparencies = transparencies;

  self.scales = _.map( [50, 75, 100, 125, 150], function( n ) {
    return {text: loc( "stamp.scale.percentage", n ),
            value: n};
  });

  self.pages = _.map(["first", "last", "all"], function(v) {
    return {text: loc(["stamp.page", v]), value: v};
  });

  self.jobId = null;
  self.jobVersion = null;
  self.updateRowValue = true;
  self.stampsChanged = ko.observable(false);

  // Stamping fields
  self.stamps = params.stamps;
  self.selectedStampsId = params.selectedStampId;
  self.selectedStamp = ko.pureComputed(function() {
    return _.find(self.stamps(), function (stamp) {
      return stamp.id === self.selectedStampsId();
    });
  });

  function stringToDate(dateString) {
    return dateString && new Date (moment(dateString, "DD.MM.YYYY"));
  }

  function ddmmyyyyDate(date) {
    return date && moment(date).format("DD.MM.YYYY");
  }

  function findRowData (type) {
    return _.chain(self.selectedStamp().rows)
      .find(function (row) { return _.some(row, {type: type}); })
      .find({type: type})
      .result("value")
      .value();
  }

  function updateRowData (type, value) {
    return _.map(self.selectedStamp().rows,  function (row) {
      return _.map(row , function(object) {
        if (object.type === type) {
          return {type: type, value: value};
        }
        return object;
      });
    });
  }

  function generatePreview() {
    return _.chain(self.selectedStamp().rows)
      .map(function (row) {
        return _.chain(row)
          .filter("value")
          .map(function(o) {return _.get(o, "value");})
          .value()
          .join(" ");
      })
      .filter(function (row) {
        return row !== "";
      })
      .value()
      .join("\n") + "\nwww.lupapiste.fi";
  }

  // Stamp info
  self.xMargin = ko.observable(self.selectedStamp().position.x.toString());
  self.xMarginOk = ko.pureComputed(function() { return util.isNum(self.xMargin()); });
  self.yMargin = ko.observable(self.selectedStamp().position.y.toString());
  self.yMarginOk = ko.pureComputed(function() { return util.isNum(self.yMargin()); });
  self.page = ko.observable(self.selectedStamp().page);
  self.transparency = ko.observable(calculateTransparency(self.selectedStamp().background));
  self.qrCode = ko.observable(self.selectedStamp().qrCode);
  self.scale = ko.observable( 100 );

  // Stamp rows
  self.customText = ko.observable(findRowData("custom-text"));
  self.extraText = ko.observable(findRowData("extra-text"));
  self.currentDate = ko.observable(stringToDate(findRowData("current-date")));
  self.verdictDate = ko.observable(stringToDate(findRowData("verdict-date")));
  self.backendId = ko.observable(findRowData("backend-id"));
  self.user = ko.observable(findRowData("user"));
  self.organization = ko.observable(findRowData("organization"));
  self.applicationId = ko.observable(findRowData("application-id"));
  self.buildingId = ko.observable(findRowData("building-id"));
  self.section = ko.observable(findRowData("section"));
  self.preview = ko.observable(generatePreview());

  if ( !self.selectedStamp().background ) {
    self.transparency(transparencies[0].value);
  }

  self.disposedComputed(function () {
    if (self.selectedStamp()) {
      self.updateRowValue = false;
      self.page(self.selectedStamp().page);
      self.xMargin(self.selectedStamp().position.x.toString());
      self.yMargin(self.selectedStamp().position.y.toString());
      self.transparency(calculateTransparency(self.selectedStamp().background));
      self.qrCode(self.selectedStamp().qrCode);
      self.customText(findRowData("custom-text"));
      self.extraText(findRowData("extra-text"));
      self.currentDate(stringToDate(findRowData("current-date")));
      self.verdictDate(stringToDate(findRowData("verdict-date")));
      self.backendId(findRowData("backend-id"));
      self.user(findRowData("user"));
      self.organization(findRowData("organization"));
      self.applicationId(findRowData("application-id"));
      self.buildingId(findRowData("building-id"));
      self.section(findRowData("section"));
      self.preview(generatePreview());
      self.updateRowValue = true;
    }
  });

  self.submit = function() {
    if (self.updateRowValue) {
      for (var i in self.stamps()) {
        if (self.stamps()[i].id === self.selectedStampsId()) {
          self.stamps()[i].position.x = _.parseInt(self.xMargin(), 10);
          self.stamps()[i].position.y = _.parseInt(self.yMargin(), 10);
          self.stamps()[i].page = self.page();
          self.stamps()[i].background = self.transparency();
          self.stamps()[i].qrCode = self.qrCode();
          self.stamps()[i].scale = self.scale();
          self.stamps()[i].rows = (updateRowData("extra-text", self.extraText()));
          self.stamps()[i].rows = (updateRowData("current-date", ddmmyyyyDate(self.currentDate())));
          self.stamps()[i].rows = (updateRowData("verdict-date", ddmmyyyyDate(self.verdictDate())));
          self.stamps()[i].rows = (updateRowData("backend-id", self.backendId()));
          self.stamps()[i].rows = (updateRowData("user", self.user()));
          self.stamps()[i].rows = (updateRowData("organization", self.organization()));
          self.stamps()[i].rows = (updateRowData("application-id", self.applicationId()));
          self.stamps()[i].rows = (updateRowData("building-id", self.buildingId()));
          self.stamps()[i].rows = (updateRowData("section", self.section()));
          self.preview(generatePreview());
          break;
        }
      }
      self.updateRowValue = true;
      self.stampsChanged(true);
    }
    return true;
  };

  _.each([self.xMargin, self.yMargin, self.transparency, self.page, self.section, self.organization, self.backendId,
          self.extraText, self.currentDate, self.verdictDate, self.buildingId, self.user, self.applicationId, self.qrCode,
          self.scale],
    function(o) {
      self.disposedSubscribe(o, self.submit);
    }
  );

/*
 * Stamp process lifecycle
 */

  var doStart = function() {
    self.status(self.statusStarting);
    ajax
      .command("stamp-attachments", {
        id: self.application.id(),
        lang: loc.getCurrentLanguage(),
        timestamp: new Date(self.currentDate()).getTime(),
        files: _.map(self.selectedFiles(), "id"),
        stamp: self.selectedStamp()
      })
      .success(self.started)
      .error(function(resp) {
        util.showSavedIndicator(resp);
        self.status(self.statusError);
      })
      .call();
    return false;
  };

  self.started = function(data) {
    self.jobId = data.job.id;
    self.jobVersion = 0;
    self.status(self.statusRunning).queryUpdate();
    return false;
  };

  self.start = function() {
    if (_.some(self.selectedFiles(), "latestVersion.stamped")) {
      hub.send("show-dialog", {ltitle: "application.restamp",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "application.restamp.confirmationMessage",
                                                 yesFn: doStart}});
    } else {
      doStart();
    }
  };

  self.queryUpdate = function() {
    ajax
      .query("stamp-attachments-job")
      .param("jobId", self.jobId)
      .param("version", self.jobVersion)
      .success(self.update)
      .call();
    return self;
  };

  self.update = function(data) {
    if (data.result === "update") {
      var update = data.job;

      self.jobVersion = update.version;
      _.each(update.value, function (data, attachmentId) {
        var newStatus = data.status;
        _(self.selectedFiles()).filter({id: attachmentId}).map(function(f) {
          f.status(newStatus);
          f.latestVersion.fileId( data.fileId );
          return f;
        }).value();
      });

      if (update.status === "done") {
        _(self.selectedFiles()).map(function(f) { return f.stamped(true); }).value();
        return self.status(self.statusDone);
      }
    }

    return self.queryUpdate();
  };

  hub.subscribe({eventType: "attachmentsService::query", stampRefresh: true}, function() {
    self.status(self.statusReady);
    pageutil.hideAjaxWait();
  });
  self.stampAgain = function() {
    pageutil.showAjaxWaitNow(loc("attachments.loading"));
    self.status(self.statusInit);
    lupapisteApp.services.attachmentsService.queryAll({stampRefresh: true});
  };

};
