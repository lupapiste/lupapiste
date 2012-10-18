/*
 * ko.init.js
 */

;(function() {

	//	
	// initialize Knockout validation
	// 
	
	ko.validation.init({
		insertMessages: true,
		decorateElement: true,
		errorMessageClass: "error-message",
	    parseInputAttributes: true,
	    messagesOnModified: true,
	    messageTemplate: "error-template",
	    registerExtenders: true
	});
	
	ko.validation.localize(loc.toMap());
	
	ko.bindingHandlers.dateString = {
	    update: function(element, valueAccessor, allBindingsAccessor, viewModel) {
			var value = ko.utils.unwrapObservable(valueAccessor());
			var date = new Date(value);
			$(element).text(date.getDate() + "." + (date.getMonth() + 1) + "." + date.getFullYear());
	    }
	};
		
	ko.bindingHandlers.dateTimeString = {
	    update: function(element, valueAccessor, allBindingsAccessor, viewModel) {
			var value = ko.utils.unwrapObservable(valueAccessor());
			var date = new Date(value);
			$(element).text(date.getDate() + "." + (date.getMonth() + 1) + "." + date.getFullYear() + " " + date.getHours() + ":" + date.getMinutes());
	    }
	};
	
    ko.bindingHandlers.ltext = {
	    update: function(element, valueAccessor, allBindingsAccessor, viewModel) {
			var value = ko.utils.unwrapObservable(valueAccessor());
			$(element).text(value && (value.length > 0) ? loc(value) : "");
	    }
	};

    ko.bindingHandlers.fullName = {
	    update: function(element, valueAccessor, allBindingsAccessor, viewModel) {
			var value = ko.utils.unwrapObservable(valueAccessor());
			var fullName = value ? value.firstName+" "+value.lastName : "";
			$(element).text(fullName); //TODO: does not work with comments in application.html
	    }
	};
		
	ko.bindingHandlers.size = {
	    update: function(element, valueAccessor, allBindingsAccessor, viewModel) {
	    	var v = ko.utils.unwrapObservable(valueAccessor());

	    	if (!v || v.length == 0) {
	    		$(element).text("");
	    		return;
	    	}
	    	
			var value = parseFloat(v);
			var unit = "B";
			
			if (value > 1200.0) {
				value = value / 1024.0;
				unit = "kB";
			}
			if (value > 1200.0) {
				value = value / 1024.0;
				unit = "MB";
			}
			if (value > 1200.0) {
				value = value / 1024.0;
				unit = "GB";
			}
			if (value > 1200.0) {
				value = value / 1024.0;
				unit = "TB"; // Future proof?
			}
			
			$(element).text(value.toFixed(1) + " " + unit);
	    }
	};
			
})();
