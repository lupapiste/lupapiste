// A table for adding and modifying building attributes to override TOS. Essentially allows to set a building secret.
// Parameters:
//   organization: the organization model for current user
LUPAPISTE.BuildingExtraAttributesModel = function (params) {
    "use strict";

    var self = this;
    var organization = params.organization;

    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    self.service = lupapisteApp.services.buildingService;
    self.hasEditPermission = lupapisteApp.models.globalAuthModel.ok("update-building");

    self.search = ko.observable("");
    self.searchTerm = self.disposedPureComputed(function() {
        return _.trim(self.search()).toLowerCase();
    });

    self.searchVtjprt = function(row) {
        self.search(row.vtjprt.value());
    };

    self.importPending = ko.observable(false);
    self.archiveUpdatePending = self.service.archiveUpdatePending;
    self.initialFetchPending = self.service.initialFetchPending;

    self.waiting = self.disposedComputed(function() {
        return self.importPending() || self.archiveUpdatePending();
    });

    self.maxBatchSize = ko.observable(params.maxBatchSize || 100);

    self.allowedForOrg = self.service.allowedForOrg;

    self.disposedComputed(function() {
        self.sendEvent("buildingService", "fetchBuildings", {organizationId: organization.organizationId()});
    });

    self.hasValue = function(field) {
        var value = ko.toJS(field);
        return Boolean(value && !_.isEmpty(value.trim()));
    };

    self.faultyRowText = function(row) {
        var vtjprt = row.vtjprt.value();
        return  _.isBlank(vtjprt)
            ? loc("building-extra-attributes.error.missing-vtjprt")
            : vtjprt;
    };

    self.hasExternalBuildingIdentifierField = function(row) {
        return self.disposedComputed(function() {
            return self.hasValue(row.vtjprt.value());
        });
    };

    self.hasError = function(row) {
        return self.disposedComputed(function() {
            return _.some(row, function(rowValue) {
                return util.getIn(rowValue, ["error"]);
            });
        });
    };

    self.canSave = function(fieldName, fieldValue) {
        return !(fieldName === "vtjprt" && _.isEmpty(_.trim(fieldValue)));
    };

    self.lastArchived = function(row) {
        return (row.sentToArchive && row.sentToArchive.value())
          ? row.sentToArchive.value().time
          : null;
    };

    self.onlySuccessFiles = function(docsByStatus) {
        return _.keys(docsByStatus).length === 1
            && !(_.isEmpty(_.get(docsByStatus, "200", {})));
    };

    self.hasErrorFiles = function(docsByStatus) {
        var onlyStatusIsNotSuccess = _.keys(docsByStatus).length === 1
            ? !_.has(docsByStatus, "200")
            : true;
        return onlyStatusIsNotSuccess;
    };

    self.archiveStatus = function(archiveUpdate) {
        var docsByStatus = _.get(archiveUpdate, "docs-by-status");
        return _.cond([
            [_.isNil,               _.constant("never-archived")],
            [_.isEmpty,             _.constant("no-files-for-building")],
            [self.onlySuccessFiles, _.constant("only-succesfully-updated-files")],
            [self.hasErrorFiles,    _.constant("has-unsuccesfully-updated-files")]
        ])(docsByStatus);
    };

    self.archiveStatusForRow = function(row) {
        var archiveUpdate = _.invoke(row, "sentToArchive.value");
        return self.archiveStatus(archiveUpdate);
    };

    self.hasArchivalAttempt = function(row) {
        return row.archiveStatus() !== "never-archived";
    };

    self.archiveReportIconCssClasses = function(row) {
        var lookup = {"has-unsuccesfully-updated-files": "lupicon-circle-attention negative",
                      "only-succesfully-updated-files":  "lupicon-circle-info positive",
                      "no-files-for-building":           "lupicon-circle-info"};
        return _.get(lookup, row.archiveStatus());
    };

    self.allRows = self.disposedComputed(function() {
        var buildingRows = self.service.buildingsAttributes();

        buildingRows.forEach(function(row) {
            row.hasExternalBuildingIdentifierField = self.hasExternalBuildingIdentifierField(row);
            row.hasError = self.hasError(row);
            row.archiveStatus = self.disposedPureComputed(function() {
                return self.archiveStatusForRow(row);
            });
            row.isSecret = self.disposedComputed(function() {
                var visibility = row.visibility.value();
                var publicity = row.publicity.value();
                return (visibility && visibility !== "julkinen") || (publicity && publicity !== "julkinen");
            });

            _.each(_.pick(row, ["ratu", "vtjprt", "kiinteistotunnus", "visibility", "publicity", "myyntipalvelussa", "address", "comment"]),
                   function(fieldData, fieldName) {
                       fieldData.value.subscribe(function(newValue) {
                           if (self.canSave(fieldName, newValue)) {
                               self.sendEvent("buildingService", "saveBuildingField", {organizationId: organization.organizationId(),
                                                                                       id: row.id.value(),
                                                                                       fieldName: fieldName,
                                                                                       fieldValue: newValue});
                           }
                       });
                       fieldData.canEdit = self.disposedPureComputed(function() {
                           var allowed = self.hasEditPermission
                                         && !self.waiting()
                                         && (fieldData.error() || !row.hasError());
                           if (fieldName === "vtjprt") {
                               return allowed;
                           }
                           return row.hasExternalBuildingIdentifierField() && allowed;
                       });
                   });
        });
        return buildingRows;
    });

    self.invalidRow = function(row) {
        return !self.hasValue(row.id.value) || !row.hasExternalBuildingIdentifierField() || row.hasError();
    };

    self.invalidRows = self.disposedPureComputed(function() {
        return _.filter(self.allRows(), self.invalidRow);
    });

    self.invalidRowExists = self.disposedPureComputed(function() {
        return _.some(self.allRows(), self.invalidRow);
    });

    self.hasField = function(fieldName) {
        return function(row) {
            var fieldObservable = row[fieldName].value;
            return fieldObservable && Boolean(fieldObservable());
        };
    };

    self.hasUnpublishedChanges = function(row) {
        var archived = self.lastArchived(row);
        var archiveStatus = row.archiveStatus();
        var modified = row.modified.value();
        var problemsInLastArchive = archiveStatus === "has-unsuccesfully-updated-files";
        var neverArchived         = archiveStatus === "never-archived";
        var unpublishedChanges = neverArchived || problemsInLastArchive || modified > archived;
        return unpublishedChanges;
    };

    self.hasArchivingErrors = function(row) {
        return row.archiveStatus() === "has-unsuccesfully-updated-files";
    };

    self.filters = {
        vtjprt:             {predicate: self.hasField("vtjprt"),           selected: ko.observable(true)},
        ratu:               {predicate: self.hasField("ratu"),             selected: ko.observable(true)},
        kiinteistotunnus:   {predicate: self.hasField("kiinteistotunnus"), selected: ko.observable(true)},
        unpublishedChanges: {predicate: self.hasUnpublishedChanges,        selected: ko.observable(true)},
        archivingErrors:    {predicate: self.hasArchivingErrors,           selected: ko.observable(true)}
    };

    self.selectedFilters = self.disposedPureComputed(function() {
        return _.filter(self.filters, function(filter) { return filter.selected(); });
    });

    self.allFiltersSelected = self.disposedComputed(function() {
        return _.isEqual(self.selectedFilters().length, _.keys(self.filters).length);
    });

    self.setAllFilters = function(value) {
        _.forEach(_.values(self.filters), function(filter) {
            filter.selected(value);
        });
    };

    self.onSelectAll = function() {
        self.setAllFilters(!self.allFiltersSelected());
        return true;
    };

    self.matchesFilter = function(row, filters) {
        return  _.some(filters, function(filter){
            return filter.predicate(row);
        });
    };

    self.searchMatchesField = function(searchTerm, row, field) {
        var fieldValue = row[field].value();
        var isMatch = (fieldValue && searchTerm)
            ? fieldValue.toLowerCase().includes(searchTerm)
            : false;
        return isMatch;
    };

    self.searchMatchesRow = function(searchTerm, row) {
        return _.isEmpty(searchTerm)
            || self.searchMatchesField(searchTerm, row, "vtjprt")
            || self.searchMatchesField(searchTerm, row, "ratu")
            || self.searchMatchesField(searchTerm, row, "kiinteistotunnus")
            || self.searchMatchesField(searchTerm, row, "address")
            || self.searchMatchesField(searchTerm, row, "comment");
    };

    self.shouldShow = function(row) {
        //Always show rows that are missing mandatory identifier fields (eg. new rows)
        //Otherwise they would never be shown because of the filters
        return !row.hasExternalBuildingIdentifierField()
            || self.matchesFilter(row, self.selectedFilters())
            && self.searchMatchesRow(self.searchTerm(), row);
    };

    self.matchingRows = self.disposedPureComputed(function() {
        return _.filter(self.allRows(), self.shouldShow);
    });

    self.rows = self.disposedPureComputed(function() {
        var start = self.pagingDataProvider.skip();
        var end = self.pagingDataProvider.skip() + self.pagingDataProvider.limit();
        return _.slice(self.matchingRows(), start, end);
    });

    self.rowsWithUnpublishedChanges = self.disposedPureComputed(function() {
        return _.filter(self.allRows(), function(row) {
            return self.filters.unpublishedChanges.predicate(row);
        });
    });

    self.hasUnpublishedRows = self.disposedPureComputed(function() {
        return !_.isEmpty(self.rowsWithUnpublishedChanges());
    });

    self.visibilityOptions = ko.observableArray(["viranomainen", "asiakas-ja-viranomainen", "julkinen"]);
    self.publicityClassOptions = ko.observableArray(["salainen", "osittain-salassapidettava", "julkinen"]);

    self.visibilityTextFn = function(item) {
        var visibilityTexts = {"asiakas-ja-viranomainen": loc("building-extra-attributes.visibility.client-and-authority"),
                               "viranomainen": loc("building-extra-attributes.visibility.authority"),
                               "julkinen": loc("building-extra-attributes.visibility.public")};
        return _.get(visibilityTexts, item, "");
    };

    self.publicityClassTextFn = function(item) {
        var publicityClassTexts = {"julkinen": loc("building-extra-attributes.publicity.public"),
                                   "osittain-salassapidettava": loc("building-extra-attributes.publicity.partly-secret"),
                                   "salainen": loc("building-extra-attributes.publicity.secret")};
        return _.get(publicityClassTexts, item, "");
    };

    self.getSuccessCount = function(updateResult) {
        return _.get(updateResult, "docs-by-status.200", []).length;
    };

    self.getDocCount = function(updateResult) {
        var docs = _(_.get(updateResult, "docs-by-status", []))
            .values()
            .flatten()
            .value();
        return docs.length;
    };

    self.noFilesToUpdate = function(historyEntry) {
        return self.archiveStatus(historyEntry) === "no-files-for-building";
    };

    self.allFilesUpdated = function(historyEntry) {
        return self.archiveStatus(historyEntry) === "only-succesfully-updated-files";
    };

    self.showUpdateReport = function(updateResults) {
        var successfullyUpdatedBuildingCount = _.filter(updateResults, self.allFilesUpdated).length;
        var totalBuildingCount = _.keys(updateResults).length;

        var noUpdatesBuildingsCount = _.filter(updateResults, self.noFilesToUpdate).length;

        var successfullyUpdatedDocCount = _.sum(_.map(updateResults, self.getSuccessCount));
        var totalDocCount = _.sum(_.map(updateResults, self.getDocCount));

        var statusColumnName = loc("building-extra-attributes.fields.sent-to-archive");
        var reportHtml =
            "<p>" + loc("building-extra-attributes.archive-update.updated-buildings", successfullyUpdatedBuildingCount, totalBuildingCount) + "</p>" +
            "<p>" + loc("building-extra-attributes.archive-update.buildings-without-docs", noUpdatesBuildingsCount) + "</p>" +
            "<p>" + loc("building-extra-attributes.archive-update.updated-docs", successfullyUpdatedDocCount, totalDocCount) + "</p>" +
            "<p>" + loc("building-extra-attributes.archive-update.row-report-instruction", statusColumnName) + "</p>";

        hub.send("show-dialog", {ltitle: "building-extra-attributes.archive-update",
                                 size: "large",
                                 component: "ok-dialog",
                                 componentParams: {text: reportHtml }});
    };


    self.getBuildingIds = function(rows) {
        return _.map(rows, function(row) {return row.id.value();});
    };

    self.updateInArchiveButtonText = function() {
        var text = loc("building-extra-attributes.send-to-archive");
        return text + " (max " + self.maxBatchSize() +  " / " + loc("building-extra-attributes.archive-update.batch-run") + ")";
    };

    self.archiveConfirmationText = function(updateableBuildingsTotal, maxBuildingsInBatch) {
        if (maxBuildingsInBatch >= updateableBuildingsTotal) {
            return loc("building-extra-attributes.archive-update.confirmation", updateableBuildingsTotal);
        }
        var currently = loc("building-extra-attributes.archive-update.currently", updateableBuildingsTotal);
        var maxBatchsizeText = loc("building-extra-attributes.archive-update.max-buildings-in-batch", maxBuildingsInBatch);
        var doYouWantToUpdateText = loc("building-extra-attributes.archive-update.confirmation", maxBuildingsInBatch);
        return currently + "<br><br>" + maxBatchsizeText +  " <br><br>" + doYouWantToUpdateText;
    };

    self.updateInArchive = function() {
        var rowsReadyForArchival = self.rowsWithUnpublishedChanges();
        var toBeArchivedRows = _.take(rowsReadyForArchival, self.maxBatchSize());
        var updateFn = function() {
            self.sendEvent("buildingService", "updateInArchive", {organizationId: organization.organizationId(),
                                                                  buildingIds: self.getBuildingIds(toBeArchivedRows),
                                                                  onResults: self.showUpdateReport});
        };
        var confirmationText = self.archiveConfirmationText(rowsReadyForArchival.length, self.maxBatchSize());
        hub.send("show-dialog", {ltitle: "building-extra-attributes.archive-update",
                                 size: "medium",
                                 component: "yes-no-dialog",
                                 componentParams: {text: confirmationText,
                                                   yesFn: updateFn}});
    };


    self.remove = function(e) {
        var removeFn = function () {
            self.sendEvent("buildingService", "removeBuildingRow", {organizationId: organization.organizationId(),
                                                                    buildingId: e.id.value()});
        };
        var message = "building-extra-attributes.removal-instruction";
        hub.send("show-dialog", {ltitle: "remove",
                                 size: "medium",
                                 component: "yes-no-dialog",
                                 componentParams: {ltext: message,
                                                   yesFn: removeFn}});
    };

    self.formatTime = function(timestamp) {
        var formattedTime = "";
        if (_.isNumber(timestamp)) {
            formattedTime = moment(timestamp).format( "D.M.YYYY HH:mm");
        }
        return formattedTime;
    };

    self.showTime = function(sentToArchiveEntry) {
        if (sentToArchiveEntry && sentToArchiveEntry.value()) {
            return self.formatTime(sentToArchiveEntry.value().time);
        } else {
            return "";
        }
    };

    self.showBuildingArchiveReport = function(row) {
        if (row.archiveStatus() === "never-archived") {
            return;
        }
        var archiveResult = _.invoke(row, "sentToArchive.value");
        var docsFoundInArchive     = self.getDocCount(archiveResult);
        var docsSuccesfullyUpdated = self.getSuccessCount(archiveResult);

        var reportHtml =
            "<p>" + loc("building-extra-attributes.archive-update.building.documents") + ": " + docsFoundInArchive + "</p>" +
            "<p>" + loc("building-extra-attributes.archive-update.updated-docs", docsSuccesfullyUpdated, docsFoundInArchive);

        hub.send("show-dialog", {ltitle:  "building-extra-attributes.archive-update.latest",
                                 size: "large",
                                 component: "ok-dialog",
                                 componentParams: {html: reportHtml}});
    };

    self.errorMsg = function(field, errorCode) {
        var locKeys = {
            "error.building.invalid-attribute": {
                "vtjprt": "building-extra-attributes.field-error.vtjprt",
                "kiinteistotunnus": "building-extra-attributes.field-error.kiinteistotunnus"
            },
            "error.building.duplicate-identifier": {
                "ratu": "building-extra-attributes.field-error.duplicate-value",
                "vtjprt": "building-extra-attributes.field-error.duplicate-value"
            }
        };
        var locKey = _.get(locKeys, [errorCode, field], "building-extra-attributes.field-error");
        var localizedText = loc(locKey);
        return localizedText;
    };


    self.showImportReport = function(response) {
        var title = _.get(response, "text");
        var ok = _.get(response, "ok");

        var reportHtml = "";

        if (ok) {
            var validBuildingsText = loc("building-extra-attributes.import.added-buildings");
            var validBuildingsCount = _.get(response, "result.valid-buildings");
            reportHtml += "<p>" + validBuildingsText +": " + validBuildingsCount + "</p>";
        } else {
            var invalidBuildingsText = loc("building-extra-attributes.import.failed-buildings");
            var failedRows = _.get(response, "result.invalid-entries", []);
            var invalidBuildingsCount = failedRows.length;
            var maxVtjprtsShown = 50;
            var failedRowVtjprts = _.map(_.take(failedRows, maxVtjprtsShown), "vtjprt");
            var showingMaxOf = failedRows.length >= maxVtjprtsShown ? "(" + loc("building-extra-attributes.import.max-number-shown") + " " + maxVtjprtsShown + ")" : "";
            reportHtml +=
                "<p>" + invalidBuildingsText +": " + invalidBuildingsCount + "</p>" +
                "<p>" + loc("building-extra-attributes.import.failed-rows") + " " + showingMaxOf + ": <br>" +
                _.join(failedRowVtjprts, "<br>") + "</p>";
        }

        hub.send("show-dialog", {ltitle:  title || "building-extra-attributes.import",
                                 size: "large",
                                 component: "ok-dialog",
                                 componentParams: {html: reportHtml}});
    };

    self.buildingsExcelDownloadUrl = function () {
        return "/lp-static/other/buildingsTemplate.xlsx";
    };

    self.allowImport = self.disposedComputed(function() {
        return self.hasEditPermission && !self.initialFetchPending() && _.isEmpty(self.allRows());
    });

    self.submitExcel = function(form) {
        var formData = new FormData(form);
        formData.append("organizationId", organization.organizationId());
        $.ajax({
            type: "POST",
            url: "/api/raw/upload-building-data",
            enctype: "multipart/form-data",
            data: formData,
            cache: false,
            contentType: false,
            processData: false,
            beforeSend: function(request) {
                self.importPending(true);
                _.each(self.headers, function(value, key) { request.setRequestHeader(key, value); });
                request.setRequestHeader("x-anti-forgery-token", $.cookie("anti-csrf-token"));
            },
            success: function(res) {
                self.sendEvent("buildingService", "fetchBuildings", {organizationId: organization.organizationId()});
                self.showImportReport(res);
            },
            error: function(res) {
                var unauthorized = _.get(res, "statusText") === "Unauthorized";
                var msg = unauthorized
                    ? "building-extra-attributes.error.unauthorized"
                    : _.get(res, "responseJSON.text", "building-extra-attributes.error.import-failed");
                hub.send("indicator", {style: "negative", message: msg});
            },
            complete: function() {
                self.importPending(false);
                form.reset();
            }
        });
    };

    self.chooseFile = function(data, event) {
        $(event.target).closest("form").find("input[name='files[]']").trigger("click");

    };

    self.fileChanged = function(data, event) {
        $(event.target).closest("form").submit();
    };

    self.filteredRowsCount = self.disposedPureComputed(function() {
        return self.matchingRows().length;
    });

    self.pagingDataProvider = {
        limit: ko.observable(10),
        skip: ko. observable(0),
        totalCount: self.filteredRowsCount,
        pending: self.waiting,
        disable: self.invalidRowExists
    };

    self.paging = {
        name: "applications-search-paging",
        params: {
            limits: [10,25,50,100],
            dataProvider: self.pagingDataProvider,
            resultsTextKey: "building-extra-attributes.buildings.results",
            itemsPerPageTextKey: "building-extra-attributes.buildings.resultsPerPage",
            toStartBtn: ko.observable(true),
            toEndBtn: ko.observable(true)
        }
    };

    self.jumpToTheLastPage = function() {
        var rowCount = self.filteredRowsCount();
        var limit = self.pagingDataProvider.limit();
        var lastPageRows = rowCount % limit || limit;
        var multiplier = (rowCount - lastPageRows) / limit;
        self.pagingDataProvider.skip(limit * multiplier);
    };

    self.addNew = function() {
        self.sendEvent("buildingService", "addNewBuildingRow", {callback: self.jumpToTheLastPage});
    };

    //A computed that we can use to to trigger side effects when results change
    self.resultChanges = self.disposedPureComputed(function() {
        return [self.searchTerm(), self.selectedFilters()];
    });

    //Reset to the first page when search criteria changes
    //We cannot subscribe to the "rows" observable since pagination manipulates that itself
    //and moving forwards or backwards would always reset us to the first page
    self.resultChanges.subscribe(function () {
        self.pagingDataProvider.skip(0);
    });

};
