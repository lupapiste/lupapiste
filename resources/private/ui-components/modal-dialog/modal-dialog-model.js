LUPAPISTE.ModalDialogModel = function (params) {
	"use strict";
	var self = this;
	self.showDialog = ko.observable(false);
	self.contentName = ko.observable();
	self.contentParams = ko.observable();

	self.closeDialog = function() {
		self.showDialog(false);
		$("html").removeClass("no-scroll");
	};

	hub.subscribe("show-dialog", function(data) {
		console.log("show", data);
		$("html").addClass("no-scroll");
		self.contentName(data.contentName);
		self.contentParams(data.contentParams);
		self.showDialog(true);
	});
};
