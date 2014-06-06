ko.bindingHandlers.accordion = {
  init: function (element, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
	  $(element).next().show();
  },
  update: function (element, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
	var slideUpTime = 300;
	var slideDownTime = 400;

	var openState = ko.utils.unwrapObservable(valueAccessor());
	var focussed = openState.focussed;
	var shouldOpen = openState.shouldOpen;

	if (focussed) {
	  var prevESrc = bindingContext.$root.prevESource;
	  if( prevESrc != null ) {
		prevESrc.removeAttribute( 'style' );
	  }

	  prevESrc = element.parentNode;
	  prevESrc.style.color = 'red';
	  bindingContext.$root.prevESource = prevESrc;

	  var parents = bindingContext.$parents;
	  if( parents.length > 1 ) {
		var secLevelSources = parents[parents.length-2].children();
		var currSource = bindingContext.$data;
		if( parents.length > 2 ) currSource = parents[parents.length-3];
		$.each( secLevelSources, function (idx, esource ) {
		  if( esource != currSource ) {
			esource.openState( { focussed : false, shouldOpen: false } );
		  }
		});
	  }
	}

	var dropDown = $(element).next();

	if (focussed && shouldOpen) {
		dropDown.slideDown(slideDownTime);
	} else if (focussed && !shouldOpen) {
		dropDown.slideUp(slideUpTime);
	} else if (!focussed && !shouldOpen) {
		dropDown.slideUp(slideUpTime);
	}
  }
};
